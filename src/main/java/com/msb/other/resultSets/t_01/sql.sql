-- 表名做了处理
<select id = "selectItemExportByQueryInfo" resultSets="financeInvoiceItemResultMapper">

SELECT bii.item_id                                                                   id,
       bli.ln_std_name                                                               stdName,
       bli.id_card                                                                   idCard,
       bli.std_no                                                                    stdNo,
       bu.unvs_name                                                                  unvsName,
       bli.grade,
       bup.pfsn_level                                                                pfsnLevel,
       bup.pfsn_name                                                                 pfsnName,
       bup.teach_method                                                              teachMethod,
       bli.std_stage                                                                 stdStage,
       bli.inclusion_status                                                          inclusionStatus,
       bli.mobile,
       CONCAT(bia.province, bia.city, bia.district, bia.street, bia.receive_address) address,
       oc.campus_name                                                                campusName,
       bii.item_code                                                                 itemCode,
       bia.apply_time                                                                applyTime,
       bso.fee_amount                                                                feeAmount,
       bso.zm_scale                                                                  zmScale,
       bso.coupon_scale                                                              couponScale,
       (bso.pay_amount + bso.demurrage_scale) AS                                     actualAmount,
       bii.status,
       IF(bsc.new_learn_id, 1, 0)             AS                                     hasChange,
       bii.export_status                                                             exportStatus
FROM bms.m bii
         LEFT JOIN bms.a bia ON bia.apply_id = bii.apply_id
         LEFT JOIN bms.i bli ON bli.learn_id = bii.sub_learn_id
         LEFT JOIN bms.u bu ON bu.unvs_id = bli.unvs_id
         LEFT JOIN bms.p bup ON bup.pfsn_id = bli.pfsn_id
         LEFT JOIN bms.s oc ON oc.campus_id = recruit_campus_id
         LEFT JOIN pay.r bso ON bso.sub_order_no = bii.sub_order_no
         LEFT JOIN bms.e bsc ON bsc.new_learn_id = bli.learn_id

</select>
