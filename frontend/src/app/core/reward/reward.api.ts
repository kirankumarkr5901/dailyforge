import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreateRewardPayload, Redemption, RedeemResponse, RefundResponse, Reward } from './reward.types';

@Injectable({ providedIn: 'root' })
export class RewardApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  list(): Observable<Reward[]> {
    return this.http.get<Reward[]>(`${this.base}/rewards`);
  }

  create(payload: CreateRewardPayload): Observable<Reward> {
    return this.http.post<Reward>(`${this.base}/rewards`, payload);
  }

  archive(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/rewards/${id}`);
  }

  redeem(id: string): Observable<RedeemResponse> {
    return this.http.post<RedeemResponse>(`${this.base}/rewards/${id}/redeem`, {});
  }

  redemptions(): Observable<Redemption[]> {
    return this.http.get<Redemption[]>(`${this.base}/reward-redemptions`);
  }

  refund(redemptionId: string): Observable<RefundResponse> {
    return this.http.delete<RefundResponse>(`${this.base}/reward-redemptions/${redemptionId}`);
  }
}
