import React, { useEffect } from 'react';
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

interface NFCPaymentModalProps {
  visible: boolean;
  onClose: () => void;
  walletAddress?: string;
}

export function NFCPaymentModal({
  visible,
  onClose,
  walletAddress = 'eip155:1:0x3D3f9852310C5B360737Af841FBb61316534db23',
}: NFCPaymentModalProps) {
  const { colors } = useTheme();
  const {
    isSupported,
    isEnabled,
    isListening,
    startListening,
    stopListening,
    openNFCSettings,
  } = useNFC({
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
            Address: {walletAddress.slice(0, 20)}...
          </Text>
          <TouchableOpacity style={styles.cancelButton} onPress={onClose}>
            <Text style={styles.cancelButtonText}>Cancel</Text>
          </TouchableOpacity>
        </View>
      </View>
    </Modal>
  );
}
