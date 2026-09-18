"use client";

import { useState } from "react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";

export interface ConfirmReportActionDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description: string;
  confirmLabel: string;
  isPending: boolean;
  onConfirm: (reason: string) => void;
}

/**
 * 제보 숨김/복구/정상 표시 확인 모달 (docs/ROADMAP.md T-33 4번).
 *
 * "숨김 버튼에 window.confirm을 쓰지 않는다"는 요구사항을 이 컴포넌트로
 * 만족한다. 사유는 선택 입력이며, 입력하면 PATCH body의 reason으로 실려
 * 나간다(docs/API.md §8).
 */
export function ConfirmReportActionDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel,
  isPending,
  onConfirm,
}: ConfirmReportActionDialogProps) {
  const [reason, setReason] = useState("");

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>

        <div className="flex flex-col gap-1.5">
          <label htmlFor="admin-action-reason" className="text-sm font-medium">
            사유 (선택)
          </label>
          <Input
            id="admin-action-reason"
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            placeholder="예: 약국 확인 결과 오기재"
          />
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isPending}>
            취소
          </Button>
          <Button onClick={() => onConfirm(reason)} disabled={isPending}>
            {isPending ? "처리 중…" : confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
