package kdt.fds.project.service;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class GoogleVisionService {

    public String extractTextFromImage(String base64Image) {
        try {
            // 1. 키 파일 이름을 현재 파일명인 "googlevision.json"으로 수정
            ClassPathResource resource = new ClassPathResource("googlevision.json");
            GoogleCredentials credentials = GoogleCredentials.fromStream(resource.getInputStream());

            // 2. 인증 설정 적용
            ImageAnnotatorSettings settings = ImageAnnotatorSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                    .build();

            // 3. 이미지 데이터 변환 (앞부분의 "data:image/jpeg;base64," 제거)
            byte[] imageBytes = Base64.getDecoder().decode(base64Image.split(",")[1]);
            ByteString imgBytes = ByteString.copyFrom(imageBytes);
            Image img = Image.newBuilder().setContent(imgBytes).build();

            Feature feat = Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build();
            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                    .addFeatures(feat)
                    .setImage(img)
                    .build();

            List<AnnotateImageRequest> requests = new ArrayList<>();
            requests.add(request);

            // 4. 클라이언트 생성 및 분석 요청
            try (ImageAnnotatorClient client = ImageAnnotatorClient.create(settings)) {
                BatchAnnotateImagesResponse response = client.batchAnnotateImages(requests);
                List<AnnotateImageResponse> responses = response.getResponsesList();

                for (AnnotateImageResponse res : responses) {
                    if (res.hasError()) return "Error: " + res.getError().getMessage();
                    return res.getFullTextAnnotation().getText();
                }
            }
        } catch (IOException e) {
            return "키 파일을 찾을 수 없습니다: googlevision.json 파일을 resources 폴더에 넣어주세요.";
        } catch (Exception e) {
            return "OCR 분석 중 오류 발생: " + e.getMessage();
        }
        return "";
    }
}