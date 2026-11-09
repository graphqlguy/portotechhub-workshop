# Appendix: alternatives to MCPJam

If you cannot run MCPJam, or would rather not sign in to it, these two clients also let a model call the tools of our MCP server:

| Client | Model | Needs |
|---|---|---|
| [ollmcp](#ollmcp-in-the-terminal), in the terminal | `qwen3:8b` from the setup, in Ollama | uv, a Python package manager. It works without an account. |
| [Claude Desktop](#claude-desktop-through-mcp-remote) | Claude | A Claude account, and Node.js from the setup |

Both connect to the backend at `http://localhost:8080/mcp`, so start the backend first.

## ollmcp, in the terminal

[ollmcp](https://github.com/jonigl/mcp-client-for-ollama) is a chat in the terminal for the models in Ollama. It sends your question to the model together with the tools of the MCP servers it connects to, and runs the tools the model calls.

1. Install [uv](https://docs.astral.sh/uv/getting-started/installation/), which runs ollmcp with `uvx` and downloads the Python version that ollmcp needs:

   | System | Command |
   |--------|---------|
   | macOS (Homebrew) | `brew install uv` |
   | Windows | `winget install --id=astral-sh.uv -e` |
   | Linux, or macOS without Homebrew | `curl -LsSf https://astral.sh/uv/install.sh \| sh` |

2. In the project folder, add our server to ollmcp under the name `petclinic`:

   ```bash
   uvx --from mcp-client-for-ollama ollmcp mcp add --transport http petclinic http://localhost:8080/mcp
   ```

   The first run downloads ollmcp, which takes a few seconds. ollmcp remembers the server for this folder, so run the next command from the same folder.

3. Start ollmcp with the chat model from the setup:

   ```bash
   uvx --from mcp-client-for-ollama ollmcp -m qwen3:8b
   ```

   It prints `Successfully connected to petclinic` with the number of tools the backend serves, and waits for a question. If you pulled `qwen3:4b` in the setup, use it in place of `qwen3:8b`.

ollmcp asks before it runs each tool the model calls. Press `Enter` to run it, or type `s` to run every tool without asking for the rest of the session. ollmcp reads the tools when it starts, so after the backend gains tools, end ollmcp with `/quit` and start it again.

> [!NOTE]
> Started with `-u http://localhost:8080/mcp`, ollmcp names the server after its address, `localhost_8080`, and each tool `localhost_8080.listPetTypes` and so on. In my runs with Ollama 0.40.14, `qwen3:8b` wrote the call to `localhost_8080.listPetTypes` correctly, but Ollama's answer to ollmcp did not contain the call, and ollmcp reported *The model produced no response or tool calls*. With the server named `petclinic`, every call reached the server.

## Claude Desktop, through mcp-remote

Claude Desktop starts a local MCP server as a program and exchanges messages with it over the program's standard input and output, the **stdio** transport. Our server speaks Streamable HTTP at `/mcp`. [mcp-remote](https://www.npmjs.com/package/mcp-remote) connects the two: Claude Desktop starts mcp-remote as its program, and mcp-remote forwards every message to `http://localhost:8080/mcp` and passes each answer back.

The connectors you add by URL in Claude's settings connect from Anthropic's servers, which cannot reach `localhost` on your laptop, so a local server needs mcp-remote.

1. Install [Claude Desktop](https://claude.ai/download) and sign in.

2. Open **Settings > Developer** and click **Edit Config**. It opens the folder with `claude_desktop_config.json`:

   | System | File |
   |--------|------|
   | macOS | `~/Library/Application Support/Claude/claude_desktop_config.json` |
   | Windows | `%APPDATA%\Claude\claude_desktop_config.json` |

3. Add the `petclinic` server. If the file already lists servers under `mcpServers`, add the `petclinic` entry next to them:

   ```json
   {
     "mcpServers": {
       "petclinic": {
         "command": "npx",
         "args": ["-y", "mcp-remote", "http://localhost:8080/mcp"]
       }
     }
   }
   ```

   mcp-remote accepts `http://` for `localhost`. For another host it also needs `--allow-http`.

4. Quit Claude Desktop and start it again. Closing the window leaves it running, so on macOS use **Claude > Quit Claude**, and on Windows right-click the Claude icon in the system tray and choose **Quit**.

5. **Settings > Developer** lists `petclinic` as running. In a new chat, the tools button below the message box lists `petclinic` with its tools.

Claude asks your permission before it runs each tool. After the backend gains tools, quit Claude Desktop and start it again, so that it lists them.

If `petclinic` shows an error, its log says why:

| System | Log |
|--------|-----|
| macOS | `~/Library/Logs/Claude/mcp-server-petclinic.log` |
| Windows | `%APPDATA%\Claude\logs\mcp-server-petclinic.log` |

| The log shows | Meaning |
|---|---|
| `Proxy established successfully between local STDIO and remote StreamableHTTPClientTransport`, then `tools/list` | mcp-remote reached the server and passed the tools to Claude Desktop. |
| `Discovering OAuth server configuration...` | mcp-remote checks whether the server asks you to sign in. Ours lets every caller in, so mcp-remote connects. |
| `ECONNREFUSED`, then `Server disconnected` | Nothing answered at `localhost:8080`. Start the backend, wait for `Started PetClinicApplication`, and restart Claude Desktop. |

The line `Using MCP server command: ... with path:` names the `npx` that Claude Desktop found and the folders it searched. mcp-remote needs Node.js 18 or newer. When the log says that `npx` or `node` cannot be found, or names an older Node.js, give the full path that `which npx` prints (`where npx` on Windows) as `"command"`, and its folder as the `PATH` of the program:

```json
"petclinic": {
  "command": "/Users/you/.nvm/versions/node/v24.10.0/bin/npx",
  "args": ["-y", "mcp-remote", "http://localhost:8080/mcp"],
  "env": { "PATH": "/Users/you/.nvm/versions/node/v24.10.0/bin:/usr/bin:/bin" }
}
```
