export async function onRequest(context) {
  const url = new URL(context.request.url);
  const backendUrl = "http://3-84-81-152.sslip.io:8750" + url.pathname + url.search;
  
  // Handle CORS preflight
  if (context.request.method === "OPTIONS") {
    return new Response(null, {
      status: 204,
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS, PATCH",
        "Access-Control-Allow-Headers": "*",
        "Access-Control-Max-Age": "86400",
      }
    });
  }

  const reqHeaders = new Headers(context.request.headers);
  reqHeaders.set("Host", "3-84-81-152.sslip.io:8750");

  const init = {
    method: context.request.method,
    headers: reqHeaders,
    redirect: "follow"
  };

  if (context.request.method !== "GET" && context.request.method !== "HEAD") {
    init.body = context.request.body;
  }

  try {
    const response = await fetch(backendUrl, init);
    const respHeaders = new Headers(response.headers);
    respHeaders.set("Access-Control-Allow-Origin", "*");
    respHeaders.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH");
    respHeaders.set("Access-Control-Allow-Headers", "*");

    return new Response(response.body, {
      status: response.status,
      statusText: response.statusText,
      headers: respHeaders
    });
  } catch (err) {
    return new Response(JSON.stringify({ detail: "Proxy Error: " + err.message }), {
      status: 502,
      headers: {
        "Content-Type": "application/json",
        "Access-Control-Allow-Origin": "*"
      }
    });
  }
}
