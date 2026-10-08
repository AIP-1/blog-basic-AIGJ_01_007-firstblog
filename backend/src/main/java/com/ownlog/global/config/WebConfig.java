package com.ownlog.global.config;

import com.ownlog.global.storage.UploadProperties;
import java.nio.file.Path;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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
    }
}
