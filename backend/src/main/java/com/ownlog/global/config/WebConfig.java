package com.ownlog.global.config;

import com.ownlog.global.storage.UploadProperties;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * /uploads/** 주소로 업로드 폴더의 파일을 보여줘요 (SecurityConfig에서 GET /uploads/**는 🔓).
 * 파일 이름이 UUID라서 같은 주소의 내용이 바뀌지 않으니 브라우저가 오래 캐시해도 돼요.
 * 배포할 때는 Nginx가 직접 제공하게 바꾸면 더 빨라요.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final UploadProperties uploadProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(uploadProperties.dir()).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic());

        // 운영(Docker)에서는 React 빌드 결과가 classpath:/static에 들어가요.
        // 있는 파일(/assets/... 등)은 그대로, 없는 화면 주소(/@아이디, /write 등)는 index.html을 줘서
        // 새로고침해도 React Router가 화면을 그려요. /api/는 제외해서 없는 API는 JSON 404가 나가요.
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver());
    }

    private static class SpaFallbackResolver extends PathResourceResolver {
        private static final Resource INDEX = new ClassPathResource("/static/index.html");

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = location.createRelative(resourcePath);
            if (requested.exists() && requested.isReadable()) {
                return requested;
            }
            boolean isApiOrFile = resourcePath.startsWith("api/") || resourcePath.contains(".");
            return isApiOrFile || !INDEX.exists() ? null : INDEX;
        }
    }
}
