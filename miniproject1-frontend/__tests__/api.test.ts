import { afterEach, describe, expect, it, vi } from "vitest";

import { ApiError, apiFetch, setAccessTokenProvider } from "@/lib/api";

/**
 * Response 대역. 실제 fetch를 띄우지 않고 상태·바디·헤더만 흉내낸다.
 * json 스텁을 밖으로 노출해 "호출되지 않았는지"까지 검증할 수 있게 한다.
 */
function mockResponse(options: {
  status: number;
  jsonBody?: unknown;
  /** true면 res.json()이 reject한다 (HTML 에러 페이지 상황) */
  jsonThrows?: boolean;
  headers?: Record<string, string>;
}) {
  const json = vi.fn(async () => {
    if (options.jsonThrows) {
      throw new SyntaxError("Unexpected token '<', \"<!DOCTYPE \"... is not valid JSON");
    }
    return options.jsonBody;
  });

  const res = {
    ok: options.status >= 200 && options.status < 300,
    status: options.status,
    headers: new Headers(options.headers),
    json,
  };

  return { res, json };
}

function stubFetch(res: unknown) {
  const fetchMock = vi.fn(async () => res as Response);
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

afterEach(() => {
  vi.unstubAllGlobals();
  setAccessTokenProvider(() => null);
});

describe("apiFetch", () => {
  it("정상 응답이면 파싱된 JSON을 반환한다", async () => {
    const body = { id: 1, displayName: "타이레놀 500mg", packageUnit: "8정" };
    const { res } = mockResponse({ status: 200, jsonBody: body });
    const fetchMock = stubFetch(res);

    const result = await apiFetch<typeof body>("/api/v1/drugs/1");

    expect(result).toEqual(body);
    expect(fetchMock).toHaveBeenCalledOnce();
    // NEXT_PUBLIC_API_BASE_URL이 없어도 기본값으로 절대 URL이 만들어진다
    expect(fetchMock.mock.calls[0][0]).toBe(
      "http://localhost:8080/api/v1/drugs/1",
    );
  });

  it("JSON 에러 바디를 ApiError로 변환한다", async () => {
    // docs/API.md §1.2 에러 포맷
    const { res } = mockResponse({
      status: 400,
      jsonBody: {
        code: "VALIDATION_FAILED",
        message: "가격은 100원 이상 200,000원 이하여야 합니다.",
        fieldErrors: [
          { field: "price", reason: "must be between 100 and 200000" },
        ],
        traceId: "9f3a1c2e",
        timestamp: "2026-09-15T13:12:33+09:00",
      },
    });
    stubFetch(res);

    const error = await apiFetch("/api/v1/price-reports", {
      method: "POST",
      body: JSON.stringify({ price: 50 }),
    }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(400);
    expect(apiError.code).toBe("VALIDATION_FAILED");
    expect(apiError.message).toBe(
      "가격은 100원 이상 200,000원 이하여야 합니다.",
    );
    expect(apiError.fieldErrors).toEqual([
      { field: "price", reason: "must be between 100 and 200000" },
    ]);
    expect(apiError.traceId).toBe("9f3a1c2e");
  });

  it("비-JSON 에러 바디여도 ApiError를 던진다", async () => {
    // Spring Security가 HTML 에러 페이지를 돌려주는 경우.
    // res.json()이 reject해도 예외 타입이 ApiError로 유지되어야 한다.
    const { res } = mockResponse({
      status: 500,
      jsonThrows: true,
      headers: { "Content-Type": "text/html" },
    });
    stubFetch(res);

    const error = await apiFetch("/api/v1/search?drugId=1").catch(
      (e: unknown) => e,
    );

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(500);
    expect(apiError.code).toBe("INTERNAL_ERROR");
    expect(apiError.message.length).toBeGreaterThan(0);
    expect(apiError.fieldErrors).toBeUndefined();
  });

  it("204 No Content면 res.json()을 부르지 않고 undefined를 반환한다", async () => {
    const { res, json } = mockResponse({ status: 204 });
    stubFetch(res);

    const result = await apiFetch<void>("/api/v1/auth/logout", {
      method: "POST",
      auth: true,
    });

    expect(result).toBeUndefined();
    expect(json).not.toHaveBeenCalled();
  });
});

describe("apiFetch 헤더 처리", () => {
  it("body가 있으면 Content-Type을 application/json으로 붙인다", async () => {
    const { res } = mockResponse({ status: 200, jsonBody: {} });
    const fetchMock = stubFetch(res);

    await apiFetch("/api/v1/price-reports", {
      method: "POST",
      body: JSON.stringify({ price: 2800 }),
    });

    const headers = fetchMock.mock.calls[0][1].headers as Headers;
    expect(headers.get("Content-Type")).toBe("application/json");
  });

  it("FormData 본문에는 Content-Type을 붙이지 않는다", async () => {
    const { res } = mockResponse({ status: 200, jsonBody: {} });
    const fetchMock = stubFetch(res);

    await apiFetch("/api/v1/uploads", {
      method: "POST",
      body: new FormData(),
    });

    const headers = fetchMock.mock.calls[0][1].headers as Headers;
    // boundary를 브라우저가 직접 넣어야 하므로 비워둬야 한다
    expect(headers.get("Content-Type")).toBeNull();
  });

  it("auth: true이고 토큰이 있으면 Authorization 헤더를 붙인다", async () => {
    const { res } = mockResponse({ status: 200, jsonBody: {} });
    const fetchMock = stubFetch(res);
    setAccessTokenProvider(() => "test-access-token");

    await apiFetch("/api/v1/auth/me", { auth: true });

    const headers = fetchMock.mock.calls[0][1].headers as Headers;
    expect(headers.get("Authorization")).toBe("Bearer test-access-token");
  });

  it("토큰이 없으면 auth: true라도 Authorization 헤더가 없다", async () => {
    const { res } = mockResponse({ status: 200, jsonBody: {} });
    const fetchMock = stubFetch(res);

    await apiFetch("/api/v1/auth/me", { auth: true });

    const headers = fetchMock.mock.calls[0][1].headers as Headers;
    expect(headers.get("Authorization")).toBeNull();
  });
});
