import { useState } from 'react';
import { Button, Dialog } from '@/components/ui';
import { SELLER_PRIVACY_NOTES, SELLER_PRIVACY_ROWS, SELLER_TERMS, SELLER_TERMS_VERSION } from '@/lib/seller-terms';

export type TermsAgreement = { terms: boolean; privacy: boolean };

type Doc = 'terms' | 'privacy';

const LABEL: Record<Doc, string> = {
  terms: '판매자 이용약관',
  privacy: '개인정보 수집·이용',
};

/**
 * 판매자 신청 전 필수 동의 두 가지 (판매자 이용약관, 개인정보 수집·이용).
 * [보기]로 전문을 창에서 읽는다. 둘 다 동의해야 신청할 수 있다. (서버도 같은 검사)
 */
export function SellerTermsAgreement({ value, onChange, error }: { value: TermsAgreement; onChange: (next: TermsAgreement) => void; error?: string }) {
  const [reading, setReading] = useState<Doc>();
  const allAgreed = value.terms && value.privacy;

  return (
    <fieldset className="flex flex-col gap-2 rounded-md border border-neutral-200 p-4">
      <legend className="px-1 text-label font-semibold text-neutral-900">약관 동의</legend>
      <label className="flex items-center gap-2 border-b border-neutral-100 pb-2 text-body font-semibold">
        <input
          type="checkbox"
          checked={allAgreed}
          onChange={(e) => onChange({ terms: e.target.checked, privacy: e.target.checked })}
          className="size-4 accent-brand-500"
        />
        모두 동의해요
      </label>
      {(['terms', 'privacy'] as const).map((doc) => (
        <div key={doc} className="flex items-center justify-between gap-2">
          <label className="flex items-center gap-2 text-body">
            <input type="checkbox" checked={value[doc]} onChange={(e) => onChange({ ...value, [doc]: e.target.checked })} className="size-4 accent-brand-500" />
            <span>
              <span className="text-red-500">[필수]</span> {LABEL[doc]}에 동의해요
            </span>
          </label>
          <Button type="button" variant="ghost" size="sm" onClick={() => setReading(doc)} aria-label={`${LABEL[doc]} 전문 보기`}>
            보기
          </Button>
        </div>
      ))}
      {error && (
        <p role="alert" className="text-label text-red-500">
          {error}
        </p>
      )}

      <Dialog
        open={reading !== undefined}
        title={reading ? LABEL[reading] : ''}
        description={`시행일 ${SELLER_TERMS_VERSION}`}
        onClose={() => setReading(undefined)}
      >
        <div className="flex flex-col gap-4 text-body">
          {reading === 'terms' ? <TermsBody /> : <PrivacyBody />}
          <Button
            type="button"
            className="self-end"
            onClick={() => {
              if (reading) onChange({ ...value, [reading]: true });
              setReading(undefined);
            }}
          >
            동의하고 닫기
          </Button>
        </div>
      </Dialog>
    </fieldset>
  );
}

function TermsBody() {
  return (
    <div className="flex flex-col gap-5">
      {SELLER_TERMS.map((chapter) => (
        <section key={chapter.title} className="flex flex-col gap-3">
          <h3 className="text-body-lg font-bold">{chapter.title}</h3>
          {chapter.articles.map((article) => (
            <div key={article.title} className="flex flex-col gap-1">
              <h4 className="font-semibold">{article.title}</h4>
              {article.body && <p className="text-neutral-700">{article.body}</p>}
              {article.items && (
                <ol className="list-decimal space-y-1 pl-5 text-neutral-700">
                  {article.items.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ol>
              )}
            </div>
          ))}
        </section>
      ))}
    </div>
  );
}

function PrivacyBody() {
  return (
    <div className="flex flex-col gap-4">
      <p className="text-neutral-700">땅땅땅 운영팀은 판매자 인증을 위해 아래와 같이 개인정보를 수집·이용합니다.</p>
      <table className="w-full border-collapse text-left">
        <thead>
          <tr className="border-b border-neutral-200 text-label text-neutral-500">
            <th className="py-2 pr-3 font-semibold">수집 항목</th>
            <th className="py-2 pr-3 font-semibold">이용 목적</th>
            <th className="py-2 font-semibold">보유 기간</th>
          </tr>
        </thead>
        <tbody>
          {SELLER_PRIVACY_ROWS.map((row) => (
            <tr key={row.item} className="border-b border-neutral-100 align-top">
              <td className="py-2 pr-3">{row.item}</td>
              <td className="py-2 pr-3">{row.purpose}</td>
              <td className="py-2">{row.period}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <ul className="list-disc space-y-1 pl-5 text-neutral-700">
        {SELLER_PRIVACY_NOTES.map((note) => (
          <li key={note}>{note}</li>
        ))}
      </ul>
    </div>
  );
}
