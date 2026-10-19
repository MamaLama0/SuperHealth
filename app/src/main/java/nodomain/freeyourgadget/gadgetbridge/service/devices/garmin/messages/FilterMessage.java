package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.messages;

public class FilterMessage extends GFDIMessage {


    private final FilterType filterType;

    public FilterMessage() {
        this(false);
    }

    /** @param allFiles also list the files already synced (archived), to recover the activity history kept on the watch */
    public FilterMessage(final boolean allFiles) {
        this.garminMessage = GarminMessage.FILTER;
        this.filterType = allFiles ? FilterType.ALL_FILES_0 : FilterType.ONLY_NEW_3;
    }

    @Override
    protected boolean generateOutgoing() {
        final MessageWriter writer = new MessageWriter(response);
        writer.writeShort(0); // packet size will be filled below
        writer.writeShort(this.garminMessage.getId());
        writer.writeByte(filterType.ordinal());

        return true;
    }

    public enum FilterType {
        ALL_FILES_0,
        DEFAULT,
        UNK_2,
        ONLY_NEW_3
    }
}
