package blocks.swagger;

import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import com.github.swagger.akka.javadsl.Converter;
import com.github.swagger.akka.javadsl.SwaggerGenerator;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static akka.http.javadsl.server.PathMatchers.segment;

public class SwaggerDocService extends AllDirectives {
    private final Set<Class<?>> apiClasses = new HashSet<>();

    private final SwaggerGenerator generator = new SwaggerGenerator() {
        private Converter converter;

        @Override
        public Converter converter() {
            if (converter == null) {
                synchronized (this) {
                    if (converter == null) {
                        converter = new Converter(this) {
                            @Override
                            public scala.collection.immutable.List<String> schemes() {
                                return scala.collection.immutable.List.<String>newBuilder().result();
                            }
                        };
                    }
                }
            }
            return converter;
        }

        @Override
        public Set<Class<?>> apiClasses() {
            return apiClasses;
        }

        @Override
        public String apiDocsPath() {
            return "api-docs";
        }

        @Override
        public String basePath() {
            return "/";
        }

        @Override
        public Info info() {
            return new Info().description("Simple akka-http application").version("1.0");
        }

        @Override
        public Map<String, SecurityScheme> securitySchemes() {
            return Collections.emptyMap();
        }

        @Override
        public Optional<ExternalDocumentation> externalDocs() {
            return Optional.empty();
        }

        @Override
        public Map<String, Object> vendorExtensions() {
            return Collections.emptyMap();
        }

        @Override
        public List<String> unwantedDefinitions() {
            return Collections.singletonList("Route");
        }

        @Override
        public String generateSwaggerJson() {
            return converter().generateSwaggerJson();
        }

        @Override
        public String generateSwaggerYaml() {
            return converter().generateSwaggerYaml();
        }
    };

    public void addApiClasses(Set<Class<?>> apiClasses) {
        this.apiClasses.addAll(apiClasses);
    }

    public Route createRoute() {
        return path(segment(generator.apiDocsPath()).slash("openapi.json"), () ->
                get(() ->
                        complete(generator.generateSwaggerJson())
                )
        );
    }
}
