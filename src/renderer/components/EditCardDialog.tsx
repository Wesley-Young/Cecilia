import { Button, FieldError, Input, Modal, TextField } from '@heroui/react';
import { useMutation } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';

import type { GroupMember } from '#core/model/contact.ts';
import { core, errorMessage } from '@/lib/rpc';

interface Props {
  group: number;
  member: GroupMember;
  onClose: () => void;
}

/** Changes a member's group card; left empty, the member shows under their nickname, which the field hints at. */
export default function EditCardDialog({ group, member, onClose }: Props) {
  const [card, setCard] = useState(member.card);
  const save = useMutation(core.contacts.setMemberCard.mutationOptions());

  const submit = (event: FormEvent) => {
    event.preventDefault();
    save.mutate({ group, uin: member.uin, card }, { onSuccess: onClose });
  };

  return (
    <Modal.Backdrop isOpen onOpenChange={(open) => !open && onClose()} isDismissable>
      <Modal.Container>
        <Modal.Dialog className="w-80">
          <form onSubmit={submit} noValidate>
            <Modal.Header>
              <Modal.Heading>修改群名片</Modal.Heading>
            </Modal.Header>
            <Modal.Body>
              <TextField aria-label="群名片" value={card} onChange={setCard} isInvalid={save.isError}>
                <Input placeholder={member.nickname} autoFocus />
                <FieldError>{save.error ? errorMessage(save.error) : undefined}</FieldError>
              </TextField>
            </Modal.Body>
            <Modal.Footer>
              <Button variant="secondary" onPress={onClose}>
                取消
              </Button>
              <Button type="submit" isPending={save.isPending}>
                保存
              </Button>
            </Modal.Footer>
          </form>
        </Modal.Dialog>
      </Modal.Container>
    </Modal.Backdrop>
  );
}
