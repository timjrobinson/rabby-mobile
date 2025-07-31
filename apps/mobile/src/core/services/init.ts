import { securityEngineService } from './shared';
import { nfcService } from './nfcService';

export async function initServices() {
  const promises = [securityEngineService.init()];

  // Initialize NFC service but don't let it break the app if it fails
  promises.push(
    nfcService.init().catch(error => {
      console.error('Failed to initialize NFC service:', error);
      // Continue without NFC functionality
    }),
  );

  return Promise.all(promises);
}
