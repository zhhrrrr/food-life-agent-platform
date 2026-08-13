package com.foodlife.trade.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodlife.trade.infrastructure.dao.po.PaymentOrderPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IPaymentOrderMapper extends BaseMapper<PaymentOrderPO> {
}
