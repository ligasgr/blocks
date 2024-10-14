package blocks.ui;

import org.apache.pekko.http.javadsl.model.ContentTypes;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;
import blocks.service.RouteCreator;

public class DefaultUiService extends AllDirectives implements RouteCreator {

    @Override
    public Route createRoute() {
        return concat(
                getFromResourceDirectory("public"),
                path("", () ->
                        get(() ->
                                getFromResource("public/index.html", ContentTypes.TEXT_HTML_UTF8)
                        )
                )
        );
    }
}
