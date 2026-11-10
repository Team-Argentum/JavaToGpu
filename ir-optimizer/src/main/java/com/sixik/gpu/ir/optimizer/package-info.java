/**
 * Оптимизатор IR: мёртвый код, математика, протяжка по локалям, CSE/LICM под бюджетом регистров, оценка давления на
 * регистры и разбиение ядер. Точка входа - {@link com.sixik.gpu.ir.optimizer.Optimizer}.
 */
package com.sixik.gpu.ir.optimizer;
