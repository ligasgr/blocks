package blocks.swagger;

import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;
import com.github.swagger.pekko.javadsl.Converter;
import com.github.swagger.pekko.javadsl.SwaggerGenerator;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.apache.pekko.http.javadsl.server.PathMatchers.segment;
import static scala.jdk.javaapi.CollectionConverters.asScala;

public final class DefaultSwaggerDocService extends AllDirectives implements SwaggerDocService {
    private final Set<Class<?>> apiClasses = new HashSet<>();
    private final Info info;
    private final String basePath;

    public DefaultSwaggerDocService(final Info info, final String basePath) {
        this.info = info;
        this.basePath = basePath;
    }

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
                                return asScala(generator.schemes()).toList();
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
            return basePath;
        }

        @Override
        public Info info() {
            return info;
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
        public List<String> schemes() {
            return Collections.emptyList();
        }

        @Override
        public String generateSwaggerYaml() {
            return converter().generateSwaggerYaml();
        }
    };

    @Override
    public void addApiClasses(final Set<Class<?>> apiClasses) {
        this.apiClasses.addAll(apiClasses);
    }

    @Override
    public Route createRoute() {
        return path(segment(generator.apiDocsPath()).slash("openapi.json"), () ->
                get(() ->
                        complete(generator.generateSwaggerJson())
                )
        );
    }
}
