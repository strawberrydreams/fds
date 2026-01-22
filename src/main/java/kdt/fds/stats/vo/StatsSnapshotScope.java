package kdt.fds.stats.vo;

/**
 * 이 파일은 스냅샷 범위를 구분하는 열거형 파일이다.
 * 일반 사용자용/관리자용 스냅샷을 분리한다.
 */
public enum StatsSnapshotScope {
    GENERAL("user", ""),
    BUSINESS("admin", "_ad");

    private final String directoryName;
    private final String fileSuffix;

    StatsSnapshotScope(String directoryName, String fileSuffix) {
        this.directoryName = directoryName;
        this.fileSuffix = fileSuffix;
    }

    public String directoryName() {
        return directoryName;
    }

    public String fileSuffix() {
        return fileSuffix;
    }
}
