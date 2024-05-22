package blocks.swagger.ui;

import org.apache.pekko.http.javadsl.model.Uri;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.scaladsl.model.StatusCodes;
import blocks.service.RouteCreator;
import org.webjars.WebJarAssetLocator;

public final class SwaggerUiService extends AllDirectives implements RouteCreator {
    private final WebJarAssetLocator locator = new WebJarAssetLocator();

    @Override
    public Route createRoute() {
        return pathPrefix("swagger-ui", () ->
                concat(
                        pathEndOrSingleSlash(() ->
                                getFromResource(locator.getFullPath("index.html"))
                        ),
                        pathEnd(() ->
                                redirect(Uri.create("/swagger-ui/"), StatusCodes.MovedPermanently())
                        ),
                        extractUnmatchedPath((path) -> {
                            try {
                                final String fullPath = locator.getFullPath(path);
                                return getFromResource(fullPath);
                            } catch (final IllegalArgumentException ie) {
                                return reject();
                            } catch (final Exception e) {
                                return failWith(e);
                            }
                        })
                )
        );
    }
}
