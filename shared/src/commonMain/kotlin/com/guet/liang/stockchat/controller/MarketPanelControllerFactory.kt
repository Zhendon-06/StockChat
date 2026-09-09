package com.guet.liang.stockchat.controller

import com.guet.liang.stockchat.data.RemoteStockMarketDetailDataSource
import com.guet.liang.stockchat.model.CapitalFlowResult
import com.guet.liang.stockchat.model.MarketChartData
import com.guet.liang.stockchat.model.MarketChartResult
import com.guet.liang.stockchat.model.MarketPeriod
import com.guet.liang.stockchat.model.TencentMarketSnapshot
import com.tencent.kuikly.core.module.NetworkModule

internal fun marketPanelController(
    network: NetworkModule,
    snapshot: TencentMarketSnapshot,
    onChartChanged: (MarketPeriod, MarketChartResult?) -> Unit,
    onCapitalChanged: (CapitalFlowResult?, MarketChartData?) -> Unit,
): MarketPanelController = MarketPanelController(
    RemoteStockMarketDetailDataSource(network), snapshot, onChartChanged, onCapitalChanged,
)
