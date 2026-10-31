import React from "react";
import ReactDOM from "react-dom/client";
import "@fontsource/silkscreen/400.css";
import "@fontsource/silkscreen/700.css";
import "@fontsource/pixelify-sans/400.css";
import "@fontsource/pixelify-sans/600.css";
import "@fontsource/inter/400.css";
import "@fontsource/inter/500.css";
import "@fontsource/inter/600.css";
import "@fontsource/inter/800.css";
import App from "./App";
import { SettingsProvider } from "./lib/settings";
import { AccountsProvider } from "./lib/accounts";
import { LaunchProvider } from "./lib/launch";
import "./styles/global.css";
import "./styles/theme.css";
import "./styles/lunar.css";
import "./styles/craft.css";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <SettingsProvider>
      <AccountsProvider>
        <LaunchProvider>
          <App />
        </LaunchProvider>
      </AccountsProvider>
    </SettingsProvider>
  </React.StrictMode>,
);
