import { Modal, Spinner } from '@heroui/react';
import { useQuery } from '@tanstack/react-query';

import SettingsForm from '@/components/SettingsForm';
import { core } from '@/lib/rpc';

export default function SettingsDialog({ isOpen, onClose }: { isOpen: boolean; onClose: () => void }) {
  const settings = useQuery({ ...core.settings.get.queryOptions(), enabled: isOpen });
  return (
    <Modal.Backdrop isOpen={isOpen} onOpenChange={(open) => !open && onClose()} isDismissable>
      <Modal.Container>
        <Modal.Dialog className="w-96">
          <Modal.Header>
            <Modal.Heading>账号设置</Modal.Heading>
          </Modal.Header>
          <Modal.Body>
            {settings.data ? (
              <SettingsForm settings={settings.data} onSaved={onClose} onCancel={onClose} />
            ) : (
              <div className="flex justify-center py-8">
                <Spinner />
              </div>
            )}
          </Modal.Body>
        </Modal.Dialog>
      </Modal.Container>
    </Modal.Backdrop>
  );
}
