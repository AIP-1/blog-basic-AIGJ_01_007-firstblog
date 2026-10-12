// 화면 점검 공통: 주소, 결과 모으기, 스크린샷 폴더, 로컬 DB SQL
import { execSync } from 'node:child_process'
import { mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

export const DIR = dirname(fileURLToPath(import.meta.url))
/** 화면 주소. 개발 서버(5173)나 Docker 이미지(화면+API 한 주소)를 가리켜요 */
export const BASE = process.env.BASE ?? 'http://localhost:5173'
export const API = `${BASE}/api`
/** 스크린샷과 업로드용 이미지 (Git에 올리지 않아요) */
export const SHOTS = join(DIR, 'screenshots')
mkdirSync(SHOTS, { recursive: true })

/** 로컬 Docker PostgreSQL에 SQL 실행 (타임캡슐 시간 당기기용). E2E_DB_SCHEMA로 스키마를 고를 수 있어요 */
export function sql(query) {
  const schema = process.env.E2E_DB_SCHEMA ? `SET search_path TO ${process.env.E2E_DB_SCHEMA}; ` : ''
  const container = process.env.E2E_DB_CONTAINER ?? 'ownlog-postgres'
  return execSync(`docker exec -i ${container} psql -U ${process.env.E2E_DB_USER ?? 'ownlog'} -d ${process.env.E2E_DB_NAME ?? 'ownlog'} -tA`,
    { input: schema + query }).toString().trim()
}
