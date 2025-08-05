import { useEffect, useState, useCallback } from 'react';
import { Platform, Alert } from 'react-native';
import { nfcService } from '@/core/services/nfcService';

export interface UseNFCOptions {
  autoStart?: boolean;
  walletAddress?: string;
  onError?: (error: Error) => void;
  onStateChanged?: (enabled: boolean) => void;
}

export function useNFC(options: UseNFCOptions = {}) {
  const { autoStart = false, walletAddress, onError, onStateChanged } = options;
  const [isEnabled, setIsEnabled] = useState(false);
  const [isListening, setIsListening] = useState(false);
  const [isSupported, setIsSupported] = useState(true);

  useEffect(() => {
    const checkNFCSupport = async () => {
      try {
        const enabled = await nfcService.checkNFCEnabled();
        setIsEnabled(enabled);
        setIsSupported(true);
      } catch (error) {
        setIsSupported(false);
      }
    };

    checkNFCSupport();

    const handleStateChanged = ({ enabled }: { enabled: boolean }) => {
      setIsEnabled(enabled);
      onStateChanged?.(enabled);
    };

    const handleError = ({ error }: { error: Error }) => {
      console.error('NFC Error:', error);
      onError?.(error);
      setIsListening(false);
    };

    nfcService.on('stateChanged', handleStateChanged);
    nfcService.on('error', handleError);

    return () => {
      nfcService.off('stateChanged', handleStateChanged);
      nfcService.off('error', handleError);
    };
  }, [onError, onStateChanged]);

  useEffect(() => {
    if (autoStart && isEnabled) {
      startListening();
    }
  }, [autoStart, isEnabled, startListening]);

  useEffect(() => {
    return () => {
      if (isListening) {
        stopListening();
      }
    };
  }, [isListening, stopListening]);

  const startListening = useCallback(async () => {
    if (!isSupported) {
      Alert.alert(
        'NFC Not Supported',
        'This device does not support NFC functionality.',
      );
      return;
    }

    if (!isEnabled) {
      Alert.alert(
        'NFC Disabled',
        'Please enable NFC in your device settings to use this feature.',
        [
          { text: 'Cancel', style: 'cancel' },
          {
            text: 'Open Settings',
            onPress: () => nfcService.requestNFCSettings(),
          },
        ],
      );
      return;
    }

    try {
      console.log('[useNFC] Starting HCE with wallet address:', walletAddress);
      setIsListening(true);
      await nfcService.startHostCardEmulation(walletAddress);
    } catch (error) {
      console.error('[useNFC] Error starting HCE:', error);
      setIsListening(false);
      onError?.(error as Error);
    }
  }, [isEnabled, isSupported, walletAddress, onError]);

  const stopListening = useCallback(async () => {
    try {
      await nfcService.stopHostCardEmulation();
      setIsListening(false);
    } catch (error) {
      console.error('Failed to stop NFC:', error);
    }
  }, []);

  const openNFCSettings = useCallback(() => {
    nfcService.requestNFCSettings();
  }, []);

  return {
    isSupported,
    isEnabled,
    isListening,
    startListening,
    stopListening,
    openNFCSettings,
  };
}
