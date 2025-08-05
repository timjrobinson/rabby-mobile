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

    console.log('[useNFCWalletSync] Hook initialized, currentAccount:', currentAccount);
    
    // Also try to get from preference service directly
    const prefAccount = preferenceService.getCurrentAccount();
    console.log('[useNFCWalletSync] Account from preference service:', prefAccount);
    
    const accountToUse = currentAccount || prefAccount;
    
    if (accountToUse?.address) {
      const walletAddress = `eip155:1:${accountToUse.address}`;
      console.log('[useNFCWalletSync] Updating NFC wallet address:', walletAddress);
      
      // Update the NFC service with the current wallet address
      nfcService.setWalletAddress(walletAddress).catch(error => {
        console.error('[useNFCWalletSync] Failed to update NFC wallet address:', error);
      });
    } else {
      console.log('[useNFCWalletSync] No account available to sync');
    }
  }, [currentAccount?.address]);
  
  // Also sync on mount
  useEffect(() => {
    if (Platform.OS !== 'android') {
      return;
    }

    const syncFromPreference = () => {
      const account = preferenceService.getCurrentAccount();
      if (account?.address) {
        const walletAddress = `eip155:1:${account.address}`;
        console.log('[useNFCWalletSync] Initial sync - updating NFC wallet address:', walletAddress);
        nfcService.setWalletAddress(walletAddress).catch(error => {
          console.error('[useNFCWalletSync] Initial sync - failed to update NFC wallet address:', error);
        });
      }
    };
    
    // Sync immediately on mount
    syncFromPreference();
  }, []);
}