import React, { useEffect, useState } from 'react';
import {
  View,
  Text,
  Modal,
  TouchableOpacity,
  ActivityIndicator,
  StyleSheet,
  Platform,
} from 'react-native';
import { useNFC } from '@/hooks/useNFC';
import { AppColorsVariants } from '@/constant/theme';
import { useTheme } from '@/hooks/theme';
import LottieView from 'lottie-react-native';
import { currentAccountAtom } from '@/hooks/account';
import { preferenceService } from '@/core/services';
import { useAtom } from 'jotai';

interface NFCPaymentModalProps {
  visible: boolean;
  onClose: () => void;
  walletAddress?: string;
}

export function NFCPaymentModal({
  visible,
  onClose,
  walletAddress,
}: NFCPaymentModalProps) {
  const { colors } = useTheme();
  const [currentAccount] = useAtom(currentAccountAtom);

  // Also try getting from preference service as a fallback
  const [fallbackAddress, setFallbackAddress] = useState<string | null>(null);
  useEffect(() => {
    const account = preferenceService.getCurrentAccount();
    if (account) {
      setFallbackAddress(account.address);
    }
  }, []);

  // Use the current account address or fall back to preference service
  const accountAddress = currentAccount?.address || fallbackAddress;
  const effectiveWalletAddress =
    walletAddress || (accountAddress ? `eip155:1:${accountAddress}` : null);

  // Log for debugging
  useEffect(() => {
    console.log('[NFCPaymentModal] Current account:', currentAccount);
    console.log('[NFCPaymentModal] Current account address:', currentAccount?.address);
    console.log('[NFCPaymentModal] Fallback address:', fallbackAddress);
    console.log('[NFCPaymentModal] Account address (combined):', accountAddress);
    console.log('[NFCPaymentModal] Effective wallet address:', effectiveWalletAddress);
    console.log('[NFCPaymentModal] Wallet address prop:', walletAddress);
  }, [currentAccount, fallbackAddress, effectiveWalletAddress, accountAddress, walletAddress]);
  const {
    isSupported,
    isEnabled,
    isListening,
    startListening,
    stopListening,
    openNFCSettings,
  } = useNFC({
    walletAddress: effectiveWalletAddress,
    onError: error => {
      console.error('NFC Error:', error);
    },
  });

  useEffect(() => {
    if (visible && isEnabled) {
      startListening();
    }
    return () => {
      if (isListening) {
        stopListening();
      }
    };
  }, [visible, isEnabled, isListening, startListening, stopListening]);

  // Payment request handling is now done at the screen level to avoid unmounting issues

  const styles = StyleSheet.create({
    container: {
      flex: 1,
      backgroundColor: 'rgba(0, 0, 0, 0.5)',
      justifyContent: 'center',
      alignItems: 'center',
    },
    content: {
      backgroundColor: colors['neutral-bg-1'],
      borderRadius: 16,
      padding: 24,
      width: '90%',
      maxWidth: 320,
      alignItems: 'center',
    },
    title: {
      fontSize: 20,
      fontWeight: '600',
      color: colors['neutral-title-1'],
      marginBottom: 16,
      textAlign: 'center',
    },
    description: {
      fontSize: 14,
      color: colors['neutral-body'],
      textAlign: 'center',
      marginBottom: 24,
      lineHeight: 20,
    },
    animationContainer: {
      width: 120,
      height: 120,
      marginBottom: 24,
    },
    button: {
      backgroundColor: colors['blue-default'],
      paddingHorizontal: 24,
      paddingVertical: 12,
      borderRadius: 8,
      marginTop: 16,
    },
    buttonText: {
      color: colors['neutral-title-2'],
      fontSize: 16,
      fontWeight: '600',
    },
    cancelButton: {
      marginTop: 12,
      paddingVertical: 8,
    },
    cancelButtonText: {
      color: colors['neutral-body'],
      fontSize: 14,
    },
    errorText: {
      color: colors['red-default'],
      fontSize: 14,
      textAlign: 'center',
      marginBottom: 16,
    },
  });

  if (!isSupported) {
    return (
      <Modal visible={visible} transparent animationType="fade">
        <View style={styles.container}>
          <View style={styles.content}>
            <Text style={styles.title}>NFC Not Supported</Text>
            <Text style={styles.description}>
              This device does not support NFC functionality.
            </Text>
            <TouchableOpacity style={styles.button} onPress={onClose}>
              <Text style={styles.buttonText}>Close</Text>
            </TouchableOpacity>
          </View>
        </View>
      </Modal>
    );
  }

  if (!isEnabled) {
    return (
      <Modal visible={visible} transparent animationType="fade">
        <View style={styles.container}>
          <View style={styles.content}>
            <Text style={styles.title}>Enable NFC</Text>
            <Text style={styles.description}>
              NFC is disabled on your device. Please enable it to receive
              payment requests.
            </Text>
            <TouchableOpacity style={styles.button} onPress={openNFCSettings}>
              <Text style={styles.buttonText}>Open Settings</Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.cancelButton} onPress={onClose}>
              <Text style={styles.cancelButtonText}>Cancel</Text>
            </TouchableOpacity>
          </View>
        </View>
      </Modal>
    );
  }

  return (
    <Modal visible={visible} transparent animationType="fade">
      <View style={styles.container}>
        <View style={styles.content}>
          <Text style={styles.title}>Ready to Receive Payment</Text>
          <View style={styles.animationContainer}>
            <ActivityIndicator size="large" color={colors['blue-default']} />
          </View>
          <Text style={styles.description}>
            Hold your phone near the NFC reader to share your wallet address.
            {'\n\n'}
            Address: {effectiveWalletAddress.slice(0, 20)}...
          </Text>
          <TouchableOpacity style={styles.cancelButton} onPress={onClose}>
            <Text style={styles.cancelButtonText}>Cancel</Text>
          </TouchableOpacity>
        </View>
      </View>
    </Modal>
  );
}
