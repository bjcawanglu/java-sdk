/*
 * Copyright 2014-2020  [fisco-dev]
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 */
package org.fisco.bcos.sdk.transaction.codec.decode;

import java.math.BigInteger;
import org.fisco.bcos.sdk.abi.datatypes.generated.tuples.generated.Tuple2;
import org.fisco.bcos.sdk.client.protocol.response.Call;
import org.fisco.bcos.sdk.contract.exceptions.ContractException;
import org.fisco.bcos.sdk.contract.precompiled.model.PrecompiledRetCode;
import org.fisco.bcos.sdk.model.RetCode;
import org.fisco.bcos.sdk.model.RevertMessageParser;
import org.fisco.bcos.sdk.model.TransactionReceipt;
import org.fisco.bcos.sdk.model.TransactionReceiptStatus;

public class ReceiptParser {
    private ReceiptParser() {}

    public static RetCode parseTransactionReceipt(TransactionReceipt receipt)
            throws ContractException {
        try {
            String status = receipt.getStatus();
            if (!"0x0".equals(status)) {
                RetCode retCode =
                        TransactionReceiptStatus.getStatusMessage(status, receipt.getMessage());
                Tuple2<Boolean, String> errorOutput =
                        RevertMessageParser.tryResolveRevertMessage(receipt);
                if (errorOutput.getValue1()) {
                    throw new ContractException(errorOutput.getValue2(), retCode.getCode());
                }
                throw new ContractException(retCode.getMessage(), retCode.getCode());
            } else {
                String output = receipt.getOutput();
                if (output.equals("0x")) {
                    return PrecompiledRetCode.CODE_SUCCESS;
                }
                try {
                    int statusValue =
                            new BigInteger(output.substring(2, output.length()), 16).intValue();
                    if (receipt.getMessage() == null || receipt.getMessage().equals("")) {
                        receipt.setMessage(PrecompiledRetCode.CODE_SUCCESS.getMessage());
                    }
                    return PrecompiledRetCode.getPrecompiledResponse(
                            statusValue, receipt.getMessage());
                } catch (Exception e) {
                    return PrecompiledRetCode.CODE_SUCCESS;
                }
            }
        } catch (NumberFormatException e) {
            throw new ContractException(
                    "NumberFormatException when parse receipt, receipt info: "
                            + receipt.toString()
                            + ", error info: "
                            + e.getMessage());
        }
    }

    public static ContractException parseExceptionCall(ContractException exception) {
        Call.CallOutput callResult = exception.getResponseOutput();
        if (callResult == null) {
            return new ContractException(exception.getMessage(), exception);
        }
        RetCode retCode = parseCallOutput(callResult, exception.getMessage());
        return new ContractException(retCode.getMessage(), retCode.getCode());
    }

    public static RetCode parseCallOutput(Call.CallOutput callResult, String message) {
        if (!callResult.getStatus().equals("0x0")) {
            Tuple2<Boolean, String> errorOutput =
                    RevertMessageParser.tryResolveRevertMessage(
                            callResult.getStatus(), callResult.getOutput());
            if (errorOutput.getValue1()) {
                return TransactionReceiptStatus.getStatusMessage(
                        callResult.getStatus(), errorOutput.getValue2());
            }
            return TransactionReceiptStatus.getStatusMessage(callResult.getStatus(), message);
        }
        try {
            int statusValue =
                    new BigInteger(
                                    callResult
                                            .getOutput()
                                            .substring(2, callResult.getOutput().length()),
                                    16)
                            .intValue();
            RetCode ret =
                    PrecompiledRetCode.getPrecompiledResponse(
                            statusValue, PrecompiledRetCode.CODE_SUCCESS.getMessage());
            return new RetCode(ret.getCode(), ret.getMessage());
        } catch (Exception e) {
            return PrecompiledRetCode.CODE_SUCCESS;
        }
    }
}