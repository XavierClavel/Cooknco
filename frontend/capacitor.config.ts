import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'eu.cooknco',
  appName: 'Cooknco',
  webDir: 'dist',
  server: {
    androidScheme: "https"
  },
  plugins: {
    App: {
      urlSchemes: ["cooknco"]
    },
    StatusBar: {
      overlaysWebView: false,
      style: "DARK",
      backgroundColor: "#ff0000ff",
    },
  }
};

export default config;
