package blocks.swagger;

import blocks.service.RouteCreator;

import java.util.Set;

public interface SwaggerDocService extends RouteCreator {
    void addApiClasses(final Set<Class<?>> apiClasses);
}
