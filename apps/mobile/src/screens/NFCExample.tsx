import React, { useState } from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { NFCPaymentModal } from '@/components/NFCPayment';
import { useTheme } from '@/hooks/theme';
import { SafeAreaView } from 'react-native-safe-area-context';

export function NFCExampleScreen() {
  const { colors } = useTheme();
  const [showNFCModal, setShowNFCModal] = useState(false);

  const styles = StyleSheet.create({
    container: {
      flex: 1,
      backgroundColor: colors['neutral-bg-1'],
    },
    content: {
      flex: 1,
      padding: 20,
      justifyContent: 'center',
      alignItems: 'center',
    },
    title: {
      fontSize: 24,
      fontWeight: '600',
      color: colors['neutral-title-1'],
      marginBottom: 16,
    },
    description: {
      fontSize: 16,
      color: colors['neutral-body'],
      textAlign: 'center',
      marginBottom: 32,
      paddingHorizontal: 20,
    },
    button: {
      backgroundColor: colors['blue-default'],
      paddingHorizontal: 32,
      paddingVertical: 16,
      borderRadius: 12,
      shadowColor: '#000',
      shadowOffset: {
        width: 0,
        height: 2,
      },
      shadowOpacity: 0.1,
      shadowRadius: 3.84,
      elevation: 5,
    },
    buttonText: {
      color: colors['neutral-title-2'],
      fontSize: 18,
      fontWeight: '600',
    },
    info: {
      marginTop: 40,
      padding: 16,
      backgroundColor: colors['neutral-card-1'],
      borderRadius: 8,
      width: '100%',
    },
    infoText: {
      fontSize: 14,
      color: colors['neutral-body'],
      lineHeight: 20,
    },
    infoTitle: {
      fontSize: 16,
      fontWeight: '600',
      color: colors['neutral-title-1'],
      marginBottom: 8,
    },
  });

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.content}>
        <Text style={styles.title}>NFC Payment</Text>
        <Text style={styles.description}>
          Tap the button below to enable NFC payment mode. Your phone will be
          ready to share your wallet address with compatible payment terminals.
        </Text>

        <TouchableOpacity
          style={styles.button}
          onPress={() => setShowNFCModal(true)}>
          <Text style={styles.buttonText}>Start NFC Payment</Text>
        </TouchableOpacity>

        <View style={styles.info}>
          <Text style={styles.infoTitle}>How it works:</Text>
          <Text style={styles.infoText}>
            1. Tap "Start NFC Payment" to enable NFC mode{'\n'}
            2. Hold your phone near a compatible NFC reader{'\n'}
            3. The reader will receive your wallet address{'\n'}
            4. Complete the payment on the terminal
          </Text>
        </View>
      </View>

      <NFCPaymentModal
        visible={showNFCModal}
        onClose={() => setShowNFCModal(false)}
      />
    </SafeAreaView>
  );
}
