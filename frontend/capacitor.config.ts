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
    }
  }
};

export default config;
