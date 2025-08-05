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

// const AID_RABBY = 'F05241424259'; // RABBY
const AID_RABBY = 'F046524545504159'; // FREEPAY

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

      // Listen for payment requests
      RabbyNFC.onPaymentRequest(uri => {
        console.log('[nfcService] Payment request received from native:', uri);
        console.log('[nfcService] Emitting paymentRequest event with:', {
          uri,
        });
        this.emit('paymentRequest', { uri });
        console.log('[nfcService] paymentRequest event emitted');
      });
    } catch (error) {
      console.error('Failed to initialize NFC:', error);
      this.emit('error', { error: error as Error });
    }
  }

  async startHostCardEmulation(walletAddress?: string) {
    console.log('[nfcService] startHostCardEmulation called with:', walletAddress);
    
    if (!this.isInitialized) {
      console.log('[nfcService] Not initialized, initializing first');
      await this.init();
    }

    try {
      if (this.isListening) {
        console.log('[nfcService] Already listening, stopping first');
        await this.stopHostCardEmulation();
      }

      console.log('[nfcService] Calling RabbyNFC.startHCE with:', walletAddress);
      await RabbyNFC.startHCE(walletAddress);
      this.isListening = true;
      this.store.lastReadTime = Date.now();
      console.log(
        '[nfcService] HCE started successfully - ready to receive payments',
      );
    } catch (error) {
      console.error('[nfcService] Failed to start NFC HCE:', error);
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

  async setWalletAddress(walletAddress: string) {
    console.log('[nfcService] Setting wallet address:', walletAddress);
    
    if (!this.isInitialized) {
      console.log('[nfcService] Not initialized, initializing first');
      await this.init();
    }

    try {
      await RabbyNFC.setWalletAddress(walletAddress);
      console.log('[nfcService] Wallet address set successfully');
    } catch (error) {
      console.error('[nfcService] Failed to set wallet address:', error);
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
