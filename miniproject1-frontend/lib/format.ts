/**
 * 표시용 포맷 유틸.
 *
 * 가격·거리·상대날짜 표기를 여기서 한 번만 정한다.
 * 화면마다 toLocaleString을 다르게 부르면 같은 값이 다르게 보인다.
 */

const KRW_FORMATTER = new Intl.NumberFormat("ko-KR");

/**
 * 원화 금액을 천단위 콤마와 "원" 단위로 표기한다.
 *
 * @example formatPrice(2800) // "2,800원"
 */
export function formatPrice(won: number): string {
  return `${KRW_FORMATTER.format(Math.round(won))}원`;
}

/**
 * "원" 없이 숫자만 천단위 콤마로 표기한다.
 * PriceTag처럼 단위를 별도 엘리먼트로 두는 경우에 쓴다.
 */
export function formatNumber(value: number): string {
  return KRW_FORMATTER.format(Math.round(value));
}

/**
 * 미터 거리를 사람이 읽는 단위로 표기한다.
 *
 * 1000m 미만은 미터, 그 이상은 소수점 한 자리 킬로미터다.
 *
 * @example formatDistance(340)  // "340m"
 * @example formatDistance(1200) // "1.2km"
 */
export function formatDistance(meters: number): string {
  if (!Number.isFinite(meters) || meters < 0) return "-";
  if (meters < 1000) return `${Math.round(meters)}m`;
  return `${(meters / 1000).toFixed(1)}km`;
}

/**
 * KST 기준 오늘 날짜를 YYYY-MM-DD로 돌려준다.
 *
 * new Date()의 로컬 날짜를 쓰면 프로세스가 UTC로 떠 있을 때
 * KST 오전 9시 이전에 하루가 어긋난다. 백엔드가 lastReportedAt을 KST 기준
 * DATE로 내려주므로(docs/API.md 시각 포맷) 프론트도 같은 기준이어야
 * 신선도 판정(30일 경계)이 서버와 일치한다. docs/DATABASE.md §8 참고.
 */
export function todayInKST(now: Date = new Date()): string {
  // en-CA 로케일은 YYYY-MM-DD 형식을 돌려준다.
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(now);
}

/** YYYY-MM-DD 문자열을 UTC 자정 기준 epoch milliseconds로 바꾼다. */
function toUtcMidnight(isoDate: string): number | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(isoDate);
  if (!match) return null;
  return Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
}

const MS_PER_DAY = 86_400_000;

/**
 * 두 날짜(YYYY-MM-DD) 사이의 일수 차를 구한다.
 * 양쪽을 UTC 자정으로 정규화해 계산하므로 서머타임·타임존 영향이 없다.
 *
 * @returns from이 to보다 과거면 양수. 파싱 실패 시 null
 */
export function daysBetween(from: string, to: string): number | null {
  const fromMs = toUtcMidnight(from);
  const toMs = toUtcMidnight(to);
  if (fromMs === null || toMs === null) return null;
  return Math.round((toMs - fromMs) / MS_PER_DAY);
}

/**
 * 날짜를 "3일 전" 형태의 상대 표기로 바꾼다.
 *
 * @param isoDate `2026-09-12` 형태의 KST 기준 날짜
 * @param today 기준일(YYYY-MM-DD). 생략하면 KST 기준 오늘.
 *              테스트에서 시스템 시계를 건드리지 않도록 인자로 받는다
 * @example formatRelativeDate("2026-09-12", "2026-09-15") // "3일 전"
 */
export function formatRelativeDate(
  isoDate: string,
  today: string = todayInKST(),
): string {
  const days = daysBetween(isoDate, today);
  if (days === null) return "-";
  if (days < 0) return "오늘"; // 미래 날짜는 표기상 오늘로 취급한다
  if (days === 0) return "오늘";
  if (days === 1) return "어제";
  return `${days}일 전`;
}
