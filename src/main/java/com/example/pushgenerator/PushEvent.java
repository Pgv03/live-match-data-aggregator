package com.example.pushgenerator;

/** One webhook event as Provider A would send it. Absent parts are omitted from the JSON. */
public record PushEvent(String deliveryId, int minute, String providerMatchId, Snap snapshot, Ball commentary) {

    public record Snap(long sequence, int revision, String status, int innings,
                       int runs, int wickets, String overs) {}

    public record Ball(String ballId, long sequence, int revision, int innings,
                       int over, int ball, String text) {}

    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"provider\":\"provider-a\"");
        sb.append(",\"deliveryId\":").append(quote(deliveryId));
        sb.append(",\"occurredAt\":").append(quote(String.format("2026-10-22T14:%02d:00.000Z", minute)));
        sb.append(",\"providerMatchId\":").append(quote(providerMatchId));
        if (snapshot != null) {
            sb.append(",\"snapshot\":{")
                    .append("\"sequence\":").append(snapshot.sequence())
                    .append(",\"revision\":").append(snapshot.revision())
                    .append(",\"status\":").append(quote(snapshot.status()))
                    .append(",\"innings\":").append(snapshot.innings())
                    .append(",\"runs\":").append(snapshot.runs())
                    .append(",\"wickets\":").append(snapshot.wickets())
                    .append(",\"overs\":").append(quote(snapshot.overs()))
                    .append("}");
        }
        if (commentary != null) {
            sb.append(",\"commentary\":{")
                    .append("\"ballId\":").append(quote(commentary.ballId()))
                    .append(",\"sequence\":").append(commentary.sequence())
                    .append(",\"revision\":").append(commentary.revision())
                    .append(",\"innings\":").append(commentary.innings())
                    .append(",\"over\":").append(commentary.over())
                    .append(",\"ball\":").append(commentary.ball())
                    .append(",\"text\":").append(quote(commentary.text()))
                    .append("}");
        }
        return sb.append("}").toString();
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}