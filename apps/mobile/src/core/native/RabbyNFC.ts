import { NativeModules, NativeEventEmitter, Platform } from 'react-native';

const { RabbyNFC } = NativeModules;

class RabbyNFCWrapper {
  private eventEmitter: NativeEventEmitter | null = null;

  constructor() {
    if (Platform.OS === 'android' && RabbyNFC) {
      this.eventEmitter = new NativeEventEmitter(RabbyNFC);
    }
  }

  isAvailable(): boolean {
    return Platform.OS === 'android' && !!RabbyNFC;
  }

  async isSupported(): Promise<boolean> {
    if (!this.isAvailable()) return false;
    try {
      return await RabbyNFC.isSupported();
    } catch (error) {
      console.error('RabbyNFC.isSupported error:', error);
      return false;
    }
  }

  async isEnabled(): Promise<boolean> {
    if (!this.isAvailable()) return false;
    try {
      return await RabbyNFC.isEnabled();
    } catch (error) {
      console.error('RabbyNFC.isEnabled error:', error);
      return false;
    }
  }

  async start(): Promise<void> {
    if (!this.isAvailable()) return;
    try {
      await RabbyNFC.start();
    } catch (error) {
      console.error('RabbyNFC.start error:', error);
      throw error;
    }
  }

  async startHCE(walletAddress?: string): Promise<void> {
    console.log('[RabbyNFC] startHCE called with address:', walletAddress);
    
    if (!this.isAvailable()) {
      throw new Error('RabbyNFC not available');
    }
    
    const addressToSend = walletAddress || '';
    console.log('[RabbyNFC] Sending to native module:', addressToSend);
    
    try {
      await RabbyNFC.startHCE(addressToSend);
      console.log('[RabbyNFC] startHCE completed successfully');
    } catch (error) {
      console.error('[RabbyNFC] startHCE error:', error);
      throw error;
    }
  }

  async stopHCE(): Promise<void> {
    if (!this.isAvailable()) return;
    try {
      await RabbyNFC.stopHCE();
    } catch (error) {
      console.error('RabbyNFC.stopHCE error:', error);
    }
  }

  async setWalletAddress(walletAddress: string): Promise<void> {
    if (!this.isAvailable()) {
      throw new Error('RabbyNFC not available');
    }
    
    console.log('[RabbyNFC] Setting wallet address:', walletAddress);
    
    try {
      await RabbyNFC.setWalletAddress(walletAddress);
      console.log('[RabbyNFC] Wallet address set successfully');
    } catch (error) {
      console.error('[RabbyNFC] setWalletAddress error:', error);
      throw error;
    }
  }

  onSuccess(callback: (data: string) => void): () => void {
    if (!this.eventEmitter) return () => {};

    const subscription = this.eventEmitter.addListener('nfcSuccess', event => {
      callback(event.data);
    });

    return () => subscription.remove();
  }

  onError(callback: (error: string) => void): () => void {
    if (!this.eventEmitter) return () => {};

    const subscription = this.eventEmitter.addListener('nfcError', event => {
      callback(event.data);
    });

    return () => subscription.remove();
  }

  onConnected(callback: (data: string) => void): () => void {
    if (!this.eventEmitter) return () => {};

    const subscription = this.eventEmitter.addListener(
      'nfcConnected',
      event => {
        callback(event);
      },
    );

    return () => subscription.remove();
  }

  onDisconnected(callback: (data: string) => void): () => void {
    if (!this.eventEmitter) return () => {};

    const subscription = this.eventEmitter.addListener(
      'nfcDisconnected',
      event => {
        callback(event);
      },
    );

    return () => subscription.remove();
  }

  onPaymentRequest(callback: (uri: string) => void): () => void {
    if (!this.eventEmitter) return () => {};

    const subscription = this.eventEmitter.addListener(
      'paymentRequest',
      event => {
        console.log('RabbyNFC: paymentRequest event received:', event);
        callback(event);
      },
    );

    return () => subscription.remove();
  }
}

export default new RabbyNFCWrapper();
