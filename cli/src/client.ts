import type { ApiError, ApiResponse, DryRunResult, GlobalOptions, JsonObject } from "./types.js";

export class CliError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "CliError";
  }
}

export class WindrunnerClient {
  private readonly apiBaseUrl: string;

  constructor(private readonly options: GlobalOptions) {
    const baseUrl = options.url.replace(/\/+$/, "");
    this.apiBaseUrl = baseUrl.endsWith("/api/v1") ? baseUrl : `${baseUrl}/api/v1`;
  }

  async get<T>(path: string): Promise<ApiResponse<T>> {
    return this.request<T>("GET", path);
  }

  async post<T>(path: string, body: unknown): Promise<ApiResponse<T> | DryRunResult> {
    return this.mutate<T>("POST", path, body);
  }

  async put<T>(path: string, body: unknown): Promise<ApiResponse<T> | DryRunResult> {
    return this.mutate<T>("PUT", path, body);
  }

  async delete(path: string): Promise<ApiResponse<null> | DryRunResult> {
    return this.mutate<null>("DELETE", path);
  }

  private async mutate<T>(method: string, path: string, body?: unknown): Promise<ApiResponse<T> | DryRunResult> {
    if (this.options.dryRun) {
      return {
        dryRun: true,
        method,
        path: `${this.apiBaseUrl}${path}`,
        ...(body === undefined ? {} : { body }),
      };
    }
    return this.request<T>(method, path, body);
  }

  private async request<T>(method: string, path: string, body?: unknown): Promise<ApiResponse<T>> {
    const apiKey = process.env.WINDRUNNER_API_KEY;
    if (!apiKey) {
      throw new CliError("WINDRUNNER_API_KEY is required for API requests.");
    }

    const headers: Record<string, string> = {
      Accept: "application/json",
      Authorization: `Bearer ${apiKey}`,
    };
    if (body !== undefined) {
      headers["Content-Type"] = "application/json";
    }

    let response: Response;
    try {
      response = await fetch(`${this.apiBaseUrl}${path}`, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      throw new CliError(`Could not connect to Windrunner: ${message}`);
    }

    const text = await response.text();
    let payload: ApiResponse<T> | JsonObject | null = null;
    if (text.trim()) {
      try {
        payload = JSON.parse(text) as ApiResponse<T> | JsonObject;
      } catch {
        throw new CliError(`Windrunner returned invalid JSON (${response.status}).`);
      }
    }

    if (!response.ok) {
      throw new CliError(formatApiFailure(response.status, payload));
    }

    if (isApiResponse(payload) && payload.errors && payload.errors.length > 0) {
      throw new CliError(formatApiErrors(payload.errors));
    }

    if (!isApiResponse(payload)) {
      return { data: payload as T | null };
    }
    return payload;
  }
}

function isApiResponse(value: unknown): value is ApiResponse<unknown> {
  return Boolean(value && typeof value === "object" && ("data" in value || "errors" in value || "meta" in value));
}

function formatApiFailure(status: number, payload: unknown): string {
  if (isApiResponse(payload) && payload.errors?.length) {
    return `Request failed (${status}): ${formatApiErrors(payload.errors)}`;
  }
  if (payload && typeof payload === "object" && "message" in payload && typeof payload.message === "string") {
    return `Request failed (${status}): ${payload.message}`;
  }
  return `Request failed (${status}).`;
}

function formatApiErrors(errors: ApiError[]): string {
  return errors
    .map((error) => {
      const prefix = error.code ? `${error.code}: ` : "";
      const field = error.field ? ` (${error.field})` : "";
      return `${prefix}${error.message ?? "Unknown API error"}${field}`;
    })
    .join("; ");
}
