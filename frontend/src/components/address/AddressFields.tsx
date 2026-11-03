import { useState } from 'react';
import { Input } from '@/components/ui';
import { formatPhoneInput, onlyDigits } from '@/lib/format';

/** 받는 사람·연락처·주소·요청사항. 연락처는 숫자만 담는다. (화면에는 하이픈을 붙여 보여 준다) */
export type DeliveryFields = {
  receiverName: string;
  receiverPhone: string;
  address: string;
  deliveryMemo: string;
};

export const EMPTY_DELIVERY: DeliveryFields = { receiverName: '', receiverPhone: '', address: '', deliveryMemo: '' };

/** 서버가 준 배송지(주문·저장된 배송지)를 입력값으로. 값이 없으면 빈 문자열 */
export function deliveryFieldsOf(source: { receiverName?: string; receiverPhone?: string; address?: string; deliveryMemo?: string } | null | undefined): DeliveryFields {
  return {
    receiverName: source?.receiverName ?? '',
    receiverPhone: onlyDigits(source?.receiverPhone ?? ''),
    address: source?.address ?? '',
    deliveryMemo: source?.deliveryMemo ?? '',
  };
}

/** 서버 검증과 같은 연락처 형식: 0으로 시작하는 9~11자리 (집 전화 02 포함) */
export function isValidReceiverPhone(phone: string): boolean {
  return /^0\d{8,10}$/.test(phone);
}

export function isDeliveryFilled(value: DeliveryFields): boolean {
  return Boolean(value.receiverName.trim() && isValidReceiverPhone(value.receiverPhone) && value.address.trim());
}

/** 주문서 배송지 입력과 배송지 관리 창이 같이 쓰는 입력 칸 (2열 grid 안에 둔다) */
export function AddressFields({ value, onChange }: { value: DeliveryFields; onChange: (next: DeliveryFields) => void }) {
  const set = (patch: Partial<DeliveryFields>) => onChange({ ...value, ...patch });
  // 입력하는 중에는 오류를 띄우지 않고, 칸을 벗어난 뒤에 형식을 알려 준다
  const [phoneTouched, setPhoneTouched] = useState(false);
  const phoneError = phoneTouched && value.receiverPhone && !isValidReceiverPhone(value.receiverPhone) ? '연락처를 다시 확인해 주세요. (0으로 시작하는 9~11자리)' : undefined;

  return (
    <>
      <Input label="받는 사람 이름" value={value.receiverName} maxLength={20} onChange={(e) => set({ receiverName: e.target.value })} required />
      <Input
        label="연락처"
        type="tel"
        inputMode="numeric"
        hint="숫자만 입력하면 하이픈은 자동으로 붙어요."
        error={phoneError}
        value={formatPhoneInput(value.receiverPhone)}
        onChange={(e) => set({ receiverPhone: onlyDigits(e.target.value).slice(0, 11) })}
        onBlur={() => setPhoneTouched(true)}
        required
      />
      <div className="col-span-2">
        <Input label="주소" value={value.address} maxLength={200} onChange={(e) => set({ address: e.target.value })} required />
      </div>
      <div className="col-span-2">
        <Input
          label="배송 요청사항 (선택)"
          placeholder="예: 문 앞에 두고 가 주세요."
          value={value.deliveryMemo}
          maxLength={100}
          onChange={(e) => set({ deliveryMemo: e.target.value })}
        />
      </div>
    </>
  );
}
