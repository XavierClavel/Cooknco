import {toHome} from "@/scripts/common";
import {Capacitor} from "@capacitor/core";

if (Capacitor.isNativePlatform()) {
  console.log("setting up capacitor listeners")
  import('@capacitor/app').then(({ App }) => {
    console.log("Capacitor app listeners loaded");
    App.addListener('appUrlOpen', ({ url }) => {
      console.log("appUrlOpen triggered:", url);

      if (url.startsWith('cooknco://login/callback')) {
        const token = new URL(url).searchParams.get('token');
        localStorage.setItem("authToken", token);
        toHome();
      }
    });
  });
}
