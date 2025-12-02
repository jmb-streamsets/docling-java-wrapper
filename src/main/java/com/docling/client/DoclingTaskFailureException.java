package com.docling.client;

/**
 * Exception thrown when an async task fails on the server side.
 */
public class DoclingTaskFailureException extends DoclingClientException {

    private final String taskId;
    private final String taskStatus;
    private final Object taskMeta;

    public DoclingTaskFailureException(String message, String taskId, String taskStatus, Object taskMeta) {
        super(message);
        this.taskId = taskId;
        this.taskStatus = taskStatus;
        this.taskMeta = taskMeta;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getTaskStatus() {
        return taskStatus;
    }

    public Object getTaskMeta() {
        return taskMeta;
    }

    @Override
    public String getMessage() {
        String base = super.getMessage();
        StringBuilder sb = new StringBuilder(base);
        if (taskId != null) {
            sb.append(" taskId=").append(taskId);
        }
        if (taskStatus != null) {
            sb.append(" status=").append(taskStatus);
        }
        if (taskMeta != null) {
            sb.append(" meta=").append(taskMeta);
        }
        return sb.toString();
    }
}
