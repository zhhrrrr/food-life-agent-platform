package com.foodlife.trade.domain.order.payment.provider;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PaymentProviderRouter {

    private final Map<String, IPaymentProvider> providers = new HashMap<>();

    public PaymentProviderRouter(List<IPaymentProvider> providerList) {
        for (IPaymentProvider provider : providerList) {
            providers.put(normalize(provider.channel()), provider);
        }
    }

    public IPaymentProvider route(String channel) {
        IPaymentProvider provider = providers.get(normalize(channel));
        if (provider == null) {
            throw new IllegalArgumentException("payment channel not supported");
        }
        return provider;
    }

    public String normalize(String channel) {
        if (channel == null || channel.trim().isEmpty()) {
            return null;
        }
        return channel.trim().toUpperCase(Locale.ROOT);
    }
}
