import { cn } from "@/lib/utils";
import { formatNumber } from "@/lib/format";

const SIZE_CLASSES = {
  sm: "text-sm",
  md: "text-base",
  lg: "text-2xl sm:text-3xl",
} as const;

export interface PriceTagProps {
  /** 원 단위 금액 */
  price: number;
  size?: keyof typeof SIZE_CLASSES;
  /** 최저가 강조. 색상만으로 정보를 전달하지 않도록 뱃지를 함께 붙인다 */
  lowest?: boolean;
  className?: string;
}

/**
 * 가격 표기.
 *
 * "원" 단위를 별도 span으로 두어 금액 숫자만 크게 보이게 한다.
 * 스크린 리더에는 "2,800원"으로 한 덩어리로 읽히도록 aria-label을 붙인다.
 */
export function PriceTag({
  price,
  size = "md",
  lowest = false,
  className,
}: PriceTagProps) {
  const formatted = formatNumber(price);

  return (
    <span
      aria-label={`${formatted}원`}
      className={cn(
        "inline-flex items-baseline gap-0.5 font-semibold tabular-nums",
        lowest ? "text-price-lowest" : "text-price",
        SIZE_CLASSES[size],
        className,
      )}
    >
      <span aria-hidden="true">{formatted}</span>
      <span aria-hidden="true" className="text-xs font-normal">
        원
      </span>
    </span>
  );
}
