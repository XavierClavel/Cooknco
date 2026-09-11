export {
  MCP_SERVER_NAME,
  mcpUrl,
  mcpClients,
}

/**
 * Where an MCP client connects, and the name it is registered under.
 *
 * Derived from the API base rather than from `window.location`: during development the app is
 * served by a dev server on another origin, while `/mcp` only ever sits next to `/api/` on the
 * deployed one (see the locations in `frontend/nginx.conf`). Vite inlines the base at build
 * time, so this follows the image: `http://localhost/mcp` for the compose stack, and whatever
 * `.env.production` names for the one CI builds.
 */
const MCP_SERVER_NAME = 'cooknco'

const mcpUrl = `${(import.meta.env.VITE_API_URL as string).replace(/\/api\/v1\/?$/, '')}/mcp`

/**
 * One entry per client we have instructions for, each with the single thing to copy.
 *
 * `key` names the two wordings in the locales — `mcp_client_<key>` for the button and
 * `mcp_client_<key>_hint` for where the copied text goes. Nothing here is specific to this
 * server beyond the URL and the name: the endpoint advertises its own authorization server, so
 * a client needs no client id, no secret and nothing pasted out of a config file.
 *
 * `icon` is a Material Design glyph, which is what the app has to hand. Set `logo` to a picture
 * in `frontend/public/` instead to show a client's own mark — the button prefers it when it is
 * there, so adding one is dropping in a file, with no change here beyond the path.
 */
type McpClient = {
  key: string
  icon: string
  logo?: string
  snippet: string
}

const mcpClients: McpClient[] = [
  {
    key: 'claude_code',
    icon: 'mdi-console',
    snippet: `claude mcp add --transport http ${MCP_SERVER_NAME} ${mcpUrl}`,
  },
  {
    key: 'claude',
    icon: 'mdi-robot-outline',
    snippet: mcpUrl,
  },
  // ChatGPT is not covered by `other` below: it connects from OpenAI's servers, so it cannot
  // shell out to a local bridge. Its wording names developer mode specifically because the
  // other way in — a deep research connector — only calls tools named `search` and `fetch`,
  // and `CookncoMcpServer` advertises `search_recipes` and `get_recipe`.
  {
    key: 'chatgpt',
    icon: 'mdi-chat-outline',
    snippet: mcpUrl,
  },
  {
    key: 'cursor',
    icon: 'mdi-cursor-default-outline',
    snippet: `{
  "mcpServers": {
    "${MCP_SERVER_NAME}": {
      "url": "${mcpUrl}"
    }
  }
}`,
  },
  {
    key: 'vscode',
    icon: 'mdi-microsoft-visual-studio-code',
    snippet: `code --add-mcp '{"name":"${MCP_SERVER_NAME}","type":"http","url":"${mcpUrl}"}'`,
  },
  {
    key: 'other',
    icon: 'mdi-connection',
    snippet: `npx -y mcp-remote ${mcpUrl}`,
  },
]
