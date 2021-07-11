package blocks.ui;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;

public class UiService extends AllDirectives {

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
