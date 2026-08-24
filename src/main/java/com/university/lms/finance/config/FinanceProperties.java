package com.university.lms.finance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Finance-module deployment posture.
 *
 * @param selfServicePaymentEnabled {@code FinanceService.payOwn} is a campus-cashier stub: it posts
 *     a real {@code PAYMENT} ledger entry with no card network and no gateway behind it. Off by
 *     default, so a real deployment cannot let a student clear their own balance and drop a
 *     financial hold for free — flip on only where the stub itself is the accepted behaviour, such
 *     as a local or demo environment.
 */
@ConfigurationProperties("lms.finance")
public record FinanceProperties(@DefaultValue("false") boolean selfServicePaymentEnabled) {}
