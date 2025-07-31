import { EventEmitter } from 'events';
import RabbyNFC from '../native/RabbyNFC';

interface NFCServiceStore {
  isEnabled: boolean;
  lastReadTime?: number;
}

export interface NFCServiceEvents {
  stateChanged: { enabled: boolean };
  tagDiscovered: { tag: any };
  error: { error: Error };
}

const AID_RABBYPAY = 'F046524545504159';

class NFCService extends EventEmitter {
  private store: NFCServiceStore = {
    isEnabled: false,
    lastReadTime: undefined,
  };
  private isInitialized = false;
  private isListening = false;

  constructor() {
    super();
  }

  async init() {
    if (this.isInitialized) return;

    try {
      // Check if native module is available
      if (!RabbyNFC.isAvailable()) {
        console.log(
          'RabbyNFC native module not available - skipping NFC initialization',
        );
        return;
      }

      const isSupported = await RabbyNFC.isSupported();
      if (!isSupported) {
        console.log('NFC not supported on this device');
        return;
      }

      await RabbyNFC.start();
      this.isInitialized = true;

      const isEnabled = await RabbyNFC.isEnabled();
      this.store.isEnabled = isEnabled;
      this.emit('stateChanged', { enabled: isEnabled });

      // Set up event listeners
      RabbyNFC.onSuccess(walletAddress => {
        console.log('NFC Success: Wallet address sent:', walletAddress);
        this.emit('walletAddressSent', { address: walletAddress });
      });

      RabbyNFC.onError(error => {
        console.error('NFC Error:', error);
        this.emit('error', { error: new Error(error) });
      });

      RabbyNFC.onConnected(data => {
        console.log('NFC Connected:', data);
        this.emit('nfcConnected', { data });
      });

      RabbyNFC.onDisconnected(data => {
        console.log('NFC Disconnected:', data);
        this.emit('nfcDisconnected', { data });
      });
    } catch (error) {
      console.error('Failed to initialize NFC:', error);
      this.emit('error', { error: error as Error });
    }
  }

  async startHostCardEmulation() {
    if (!this.isInitialized) {
      await this.init();
    }

    try {
      if (this.isListening) {
        await this.stopHostCardEmulation();
      }

      await RabbyNFC.startHCE();
      this.isListening = true;
      this.store.lastReadTime = Date.now();
      console.log(
        'NFC Host Card Emulation started - ready to receive payments',
      );
    } catch (error) {
      console.error('Failed to start NFC HCE:', error);
      this.emit('error', { error: error as Error });
      this.isListening = false;
    }
  }

  async stopHostCardEmulation() {
    if (!this.isListening) return;

    try {
      await RabbyNFC.stopHCE();
      this.isListening = false;
      console.log('NFC Host Card Emulation stopped');
    } catch (error) {
      console.error('Failed to stop NFC HCE:', error);
    }
  }

  async checkNFCEnabled(): Promise<boolean> {
    try {
      return await RabbyNFC.isEnabled();
    } catch (error) {
      return false;
    }
  }

  async requestNFCSettings() {
    try {
      // On Android, we can use an intent to open NFC settings
      console.log(
        'Opening NFC settings is not implemented in native module yet',
      );
    } catch (error) {
      console.error('Failed to open NFC settings:', error);
    }
  }

  destroy() {
    this.stopHostCardEmulation();
    this.removeAllListeners();
  }
}

export const nfcService = new NFCService();
