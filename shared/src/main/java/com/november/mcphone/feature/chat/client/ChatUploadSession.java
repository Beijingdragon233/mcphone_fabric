package com.november.mcphone.feature.chat.client;

/** 上传任务归属：只允许当前任务回到启动时的连接，取消后旧后台结果不得继续发包或结束新任务。 */
final class ChatUploadSession {
    static final class Ticket {
        private final Object connection;

        Ticket(Object connection) {
            this.connection = connection;
        }

        public Object connection() { return connection; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Ticket)) return false;
            Ticket that = (Ticket) other;
            return java.util.Objects.equals(this.connection, that.connection);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(connection);
            return hash;
        }

        @Override public String toString() { return "Ticket[connection=" + connection + "]"; }
}
    private Ticket active;

    Ticket start(Object connection) {
        if (connection == null) throw new IllegalArgumentException("上传需要有效连接");
        active = new Ticket(connection);
        return active;
    }

    boolean accepts(Ticket ticket, Object currentConnection) {
        return ticket != null && ticket == active && currentConnection != null
                && ticket.connection() == currentConnection;
    }

    void cancel() { active = null; }
}
