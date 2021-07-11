package blocks.swagger.ui;

import akka.http.javadsl.model.Uri;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import akka.http.scaladsl.model.StatusCodes;
import org.webjars.WebJarAssetLocator;

public class SwaggerUiService extends AllDirectives {
    private final WebJarAssetLocator locator = new WebJarAssetLocator();

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
                                String fullPath = locator.getFullPath(path);
                                return getFromResource(fullPath);
                            } catch (IllegalArgumentException ie) {
                                return reject();
                            } catch (Exception e) {
                                return failWith(e);
                            }
                        })
                )
        );
    }
}
