package com.ownlog.auth.oauth;

import java.util.ArrayList;
import java.util.List;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.stereotype.Component;

/**
 * 키가 들어 있는 서비스만 골라서 OAuth2 "클라이언트 등록 정보"를 만들어요.
 * 구글·깃허브는 Spring이 주소를 알고 있고(CommonOAuth2Provider), 카카오·네이버는 주소를 직접 적어요.
 *
 * Spring Boot의 spring.security.oauth2.client.registration 설정을 쓰지 않는 이유: 그 방식은 client-id가 비어 있으면
 * 서버가 아예 뜨지 않아요. 우리는 "등록한 서비스만 버튼이 보이고, 하나도 없어도 정상 동작"하게 하고 싶었어요.
 */
@Component
public class OAuthClientRegistrations {

    private final List<ClientRegistration> registrations;

    public OAuthClientRegistrations(OAuthProperties properties) {
        String redirectUri = (properties.publicUrl() == null || properties.publicUrl().isBlank()
                ? "{baseUrl}" : properties.publicUrl().replaceAll("/+$", ""))
                + "/login/oauth2/code/{registrationId}";
        List<ClientRegistration> list = new ArrayList<>();
        for (SocialProvider provider : SocialProvider.values()) {   // 버튼 순서도 이 순서예요
            OAuthProperties.Client client = properties.clients().get(provider.id());
            if (client != null && client.isConfigured()) {
                list.add(build(provider, client, redirectUri));
            }
        }
        this.registrations = List.copyOf(list);
    }

    public List<ClientRegistration> all() {
        return registrations;
    }

    public List<String> enabledProviderIds() {
        return registrations.stream().map(ClientRegistration::getRegistrationId).toList();
    }

    private static ClientRegistration build(SocialProvider provider, OAuthProperties.Client client, String redirectUri) {
        ClientRegistration.Builder builder = switch (provider) {
            // 이메일 권한은 따로 심사가 필요할 수 있어서, 닉네임(필수 동의)만 받아요
            case KAKAO -> ClientRegistration.withRegistrationId("kakao")
                    .clientName("카카오")
                    .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                    .tokenUri("https://kauth.kakao.com/oauth/token")
                    .userInfoUri("https://kapi.kakao.com/v2/user/me")
                    .userNameAttributeName("id")
                    .scope("profile_nickname")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST);
            // 네이버는 회원 정보를 {"response": {...}} 안에 넣어서 줘요
            case NAVER -> ClientRegistration.withRegistrationId("naver")
                    .clientName("네이버")
                    .authorizationUri("https://nid.naver.com/oauth2.0/authorize")
                    .tokenUri("https://nid.naver.com/oauth2.0/token")
                    .userInfoUri("https://openapi.naver.com/v1/nid/me")
                    .userNameAttributeName("response")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST);
            // openid를 빼서 일반 OAuth2로 받아요 (회원 정보 주소에서 sub·email·name을 받아요)
            case GOOGLE -> CommonOAuth2Provider.GOOGLE.getBuilder("google")
                    .clientName("구글")
                    .scope("profile", "email");
            case GITHUB -> CommonOAuth2Provider.GITHUB.getBuilder("github")
                    .clientName("깃허브")
                    .scope("read:user");
        };
        return builder.clientId(client.clientId().strip())
                .clientSecret(client.clientSecret().strip())
                .redirectUri(redirectUri)
                .build();
    }
}
