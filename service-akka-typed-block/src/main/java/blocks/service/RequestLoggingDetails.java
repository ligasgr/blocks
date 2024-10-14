package blocks.service;

import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.RemoteAddress;

public final class RequestLoggingDetails {
    public final RemoteAddress address;
    public final HttpRequest request;
    public final int statusCode;
    public final boolean streamed;
    public final long timeInNano;

    public RequestLoggingDetails(final RemoteAddress address, final HttpRequest request, final int statusCode, final boolean streamed, final long timeInNano) {
        this.address = address;
        this.request = request;
        this.statusCode = statusCode;
        this.streamed = streamed;
        this.timeInNano = timeInNano;
    }

    @Override
    public String toString() {
        return "RequestLoggingDetails{" +
                "address=" + address +
                ", request=" + request +
                ", statusCode=" + statusCode +
                ", streamed=" + streamed +
                ", timeInNano=" + timeInNano +
                '}';
    }
}
