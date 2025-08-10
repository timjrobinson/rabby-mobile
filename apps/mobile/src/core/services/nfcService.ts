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

const AID_RABBY = 'F05241424259'; // RABBY

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
        return;
      }

      const isSupported = await RabbyNFC.isSupported();
      if (!isSupported) {
        return;
      }

      await RabbyNFC.start();
      this.isInitialized = true;

      const isEnabled = await RabbyNFC.isEnabled();
      this.store.isEnabled = isEnabled;
      this.emit('stateChanged', { enabled: isEnabled });

      // Set up event listeners
      RabbyNFC.onSuccess(walletAddress => {
        this.emit('walletAddressSent', { address: walletAddress });
      });

      RabbyNFC.onError(error => {
        this.emit('error', { error: new Error(error) });
      });

      RabbyNFC.onConnected(data => {
        this.emit('nfcConnected', { data });
      });

      RabbyNFC.onDisconnected(data => {
        this.emit('nfcDisconnected', { data });
      });

      // Listen for payment requests
      RabbyNFC.onPaymentRequest(uri => {
        this.emit('paymentRequest', { uri });
      });
    } catch (error) {
      this.emit('error', { error: error as Error });
    }
  }

  async startHostCardEmulation(walletAddress?: string) {
    if (!this.isInitialized) {
      await this.init();
    }

    try {
      if (this.isListening) {
        await this.stopHostCardEmulation();
      }

      await RabbyNFC.startHCE(walletAddress);
      this.isListening = true;
      this.store.lastReadTime = Date.now();
    } catch (error) {
      this.emit('error', { error: error as Error });
      this.isListening = false;
    }
  }

  async stopHostCardEmulation() {
    if (!this.isListening) return;

    try {
      await RabbyNFC.stopHCE();
      this.isListening = false;
    } catch (error) {
      // Silent fail
    }
  }

  async setWalletAddress(walletAddress: string) {
    if (!this.isInitialized) {
      await this.init();
    }

    try {
      await RabbyNFC.setWalletAddress(walletAddress);
    } catch (error) {
      throw error;
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
    // On Android, we can use an intent to open NFC settings
    // Not implemented in native module yet
  }

  destroy() {
    this.stopHostCardEmulation();
    this.removeAllListeners();
  }
}

export const nfcService = new NFCService();
