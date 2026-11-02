/// <reference lib="dom" />
import { ipcRenderer } from 'electron';

// The preload's only job: pass the MessagePort to the account process on to the page. Nothing else is exposed;
// everything the page does goes through that port or app://.
ipcRenderer.on('cecilia:core-port', (event) => {
  window.postMessage('cecilia:core-port', window.location.origin, event.ports);
});
