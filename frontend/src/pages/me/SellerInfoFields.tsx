import { Input, Notice, Select } from '@/components/ui';
import { onlyDigits } from '@/lib/format';
import { BANK_LABEL } from '@/lib/labels';
import { ACCOUNT_HOLDER_MAX, RETURN_ADDRESS_MAX, type SellerInfoErrors, type SellerInfoValues } from '@/lib/seller';

const BANK_OPTIONS = [{ value: '', label: '은행 선택' }, ...Object.entries(BANK_LABEL).map(([value, label]) => ({ value, label }))];

/**
 * 판매자 신청의 정산 계좌(비공개)와 반품·교환 주소·고객 문의 연락처(프로필에 공개).
 * realName: 본인인증 실명. 예금주는 이 이름과 같아야 한다. (인증 전이면 없음)
 */
export function SellerInfoFields({
  values,
  errors,
  realName,
  onChange,
}: {
  values: SellerInfoValues;
  errors: SellerInfoErrors;
  realName?: string;
  onChange: (patch: Partial<SellerInfoValues>) => void;
}) {
  return (
    <>
      <fieldset className="flex flex-col gap-3">
        <legend className="mb-1.5 text-label font-semibold text-neutral-900">정산 계좌</legend>
        <Notice>
          {realName
            ? `예금주는 본인인증한 이름(${realName})과 같아야 해요. 본인 명의 계좌만 등록할 수 있어요.`
            : '예금주는 본인인증한 이름과 같아야 해요. 본인 명의 계좌만 등록할 수 있어요.'}{' '}
          계좌 정보는 나와 관리자만 볼 수 있어요.
        </Notice>
        <div className="grid grid-cols-[200px_1fr_200px] gap-3">
          <Select label="은행" options={BANK_OPTIONS} value={values.bankCode} onChange={(e) => onChange({ bankCode: e.target.value })} error={errors.bankCode} />
          <Input
            label="계좌번호"
            inputMode="numeric"
            placeholder="숫자와 하이픈(-)만"
            maxLength={20}
            autoComplete="off"
            value={values.accountNumber}
            onChange={(e) => onChange({ accountNumber: e.target.value.replace(/[^0-9-]/g, '') })}
            error={errors.accountNumber}
          />
          <Input
            label="예금주"
            maxLength={ACCOUNT_HOLDER_MAX}
            autoComplete="off"
            placeholder={realName}
            value={values.accountHolder}
            onChange={(e) => onChange({ accountHolder: e.target.value })}
            error={errors.accountHolder}
          />
        </div>
      </fieldset>

      <fieldset className="flex flex-col gap-3">
        <legend className="mb-1.5 text-label font-semibold text-neutral-900">반품·교환 주소와 고객 문의 연락처</legend>
        <Notice>이 주소와 연락처는 판매자 인증을 받으면 판매자 프로필에 공개되어 다른 회원 누구나 볼 수 있어요.</Notice>
        <Input
          label="반품·교환 받을 주소"
          placeholder="예) 서울시 중구 세종대로 110, 2층 땅땅상점"
          maxLength={RETURN_ADDRESS_MAX}
          value={values.returnAddress}
          onChange={(e) => onChange({ returnAddress: e.target.value })}
          error={errors.returnAddress}
        />
        <div className="w-[320px]">
          <Input
            label="고객 문의 연락처"
            inputMode="numeric"
            placeholder="하이픈 없이 숫자만 (예: 0212345678)"
            maxLength={11}
            value={values.contactPhone}
            onChange={(e) => onChange({ contactPhone: onlyDigits(e.target.value) })}
            error={errors.contactPhone}
          />
        </div>
      </fieldset>
    </>
  );
}
