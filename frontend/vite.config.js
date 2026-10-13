import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // 브라우저는 5173에만 요청하고, Vite가 Spring(8080)으로 대신 전달해요 → CORS가 생기지 않아요
    proxy: {
      '/api': 'http://localhost:8080',
      '/uploads': 'http://localhost:8080',
      // 소셜 로그인: 동의 화면으로 보내는 주소와 서비스에서 돌아오는 주소. Host(localhost:5173)를 그대로 넘겨서
      // 서버가 돌아올 주소를 http://localhost:5173/login/oauth2/code/…로 만들어요 (React의 /login 화면과는 다른 주소예요)
      '/oauth2': 'http://localhost:8080',
      '/login/oauth2': 'http://localhost:8080',
    },
  },
})
