import { useEffect } from 'react';
import { useAtom } from 'jotai';
import { currentAccountAtom } from '@/hooks/account';
import { nfcService } from '@/core/services/nfcService';
import { preferenceService } from '@/core/services';
import { Platform } from 'react-native';

/**
 * Hook to sync the current wallet address with the NFC service
 * This ensures the NFC HCE service always has the current wallet address
 */
export function useNFCWalletSync() {
  const [currentAccount] = useAtom(currentAccountAtom);

  useEffect(() => {
    if (Platform.OS !== 'android') {
      return;
    }

    // Also try to get from preference service directly
    const prefAccount = preferenceService.getCurrentAccount();
    const accountToUse = currentAccount || prefAccount;

    if (accountToUse?.address) {
      const walletAddress = `eip155:1:${accountToUse.address}`;

      // Update the NFC service with the current wallet address
      nfcService.setWalletAddress(walletAddress).catch(() => {
        // Silent fail
      });
    }
  }, [currentAccount]);
}
