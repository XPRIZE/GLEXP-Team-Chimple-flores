package org.chimple.flores.multicast;

public class MessageStatus {
    private boolean isOutOfSyncMessage = false;
    private boolean isDuplicateMessage = false;

    public MessageStatus(boolean isOutOfSyncMessage, boolean isDuplicateMessage) {
        this.isDuplicateMessage = isDuplicateMessage;
        this.isOutOfSyncMessage = isOutOfSyncMessage;
    }

    public boolean isOutOfSyncMessage() {
        return isOutOfSyncMessage;
    }

    public void setOutOfSyncMessage(boolean outOfSyncMessage) {
        isOutOfSyncMessage = outOfSyncMessage;
    }

    public boolean isDuplicateMessage() {
        return isDuplicateMessage;
    }

    public void setDuplicateMessage(boolean duplicateMessage) {
        isDuplicateMessage = duplicateMessage;
    }
}
